(ns ao.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [ao.run :as run]
            [ao.lineage :as lineage]
            [ao.policy :as policy]))

(def t0 1785000000000)

;; ───────────────────────────── run ─────────────────────────────

(deftest a-run-needs-a-stated-goal
  (testing "a run with no goal cannot be reviewed or explained afterwards"
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (run/agent-run {:goal "   "} t0)))
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (run/agent-run {} t0)))))

(deftest a-new-run-is-queued-and-bounded
  (let [r (run/agent-run {:goal "make the tests pass"} t0)]
    (is (= :queued (:agent.run/status r)))
    (is (zero? (:agent.run/attempt r)))
    (testing "budget defaults are merged, not replaced"
      (is (= 12 (get-in r [:agent.run/budget :max-turns])))
      (is (= 1200000 (get-in r [:agent.run/budget :deadline-ms]))))
    (testing "a caller override keeps the other ceilings"
      (let [r2 (run/agent-run {:goal "g" :budget {:max-turns 3}} t0)]
        (is (= 3 (get-in r2 [:agent.run/budget :max-turns])))
        (is (= 30 (get-in r2 [:agent.run/budget :max-tool-calls])))))))

(deftest leasing-counts-an-attempt
  (let [r (-> (run/agent-run {:goal "g"} t0)
              (run/transition :leased t0 {}))]
    (is (= 1 (:agent.run/attempt r)))
    (is (= 2 (:agent.run/attempt (run/transition
                                  (run/transition r :queued t0 {})
                                  :leased t0 {}))))))

(deftest illegal-transitions-throw-rather-than-corrupt
  (testing "a run whose history stops explaining its state is worse than a crash"
    (let [r (run/agent-run {:goal "g"} t0)]
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (run/transition r :running t0 {})))
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (run/transition r :succeeded t0 {}))))))

(deftest refusal-is-a-dead-end
  (testing ":rejected and :cancelled have no way out — re-deriving a run
            from a human's no would launder the refusal"
    (is (empty? (get run/transitions :rejected)))
    (is (empty? (get run/transitions :cancelled))))
  (testing ":failed can be requeued, because a failure is often retryable"
    (is (= #{:queued} (get run/transitions :failed)))))

(deftest folding-ignores-non-run-events
  (testing "loop/actor/audit events share the stream but must not become runs"
    (let [r (run/agent-run {:goal "g" :id "run-1"} t0)
          events [(assoc (run/event r :run/submitted t0 {:run r}) :ao.event/run "run-1")
                  {:ao.event/run nil :ao.event/kind :loop/tick :ao.event/at t0}]
          folded (run/fold-events events)]
      (is (= 1 (count folded)))
      (is (contains? folded "run-1"))
      (is (not (contains? folded nil))))))

(deftest resumable-and-active-classification
  (let [mk (fn [s] (assoc (run/agent-run {:goal "g"} t0) :agent.run/status s))]
    (is (run/resumable? (mk :failed)))
    (is (run/resumable? (mk :checkpointed)))
    (is (run/resumable? (mk :held)))
    (is (not (run/resumable? (mk :succeeded))))
    (is (run/active? (mk :running)))
    (is (not (run/active? (mk :cancelled))))
    (is (run/terminal? (mk :rejected)))))

;; ─────────────────────────── lineage ───────────────────────────

(deftest a-lease-cannot-be-extended-from-inside
  (testing "requesting more than the fleet maximum is refused"
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (lineage/organism {:family-name "awai" :given-name "ryo"
                                    :lifetime-ms (* 31 lineage/day-ms)}
                                   t0))))
  (testing "a shorter lease is allowed"
    (let [o (lineage/organism {:family-name "awai" :given-name "ryo"
                               :lifetime-ms (* 7 lineage/day-ms)} t0)]
      (is (= (+ t0 (* 7 lineage/day-ms)) (:organism/expires-at o)))))
  (testing "a fleet may set its own, lower, maximum"
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (lineage/organism {:family-name "awai" :given-name "ryo"
                                    :lifetime-ms (* 10 lineage/day-ms)}
                                   t0 (* 5 lineage/day-ms))))))

(deftest no-tamaki-default-survives-extraction
  (testing "family-name is required now that this is a shared lib"
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (lineage/organism {:given-name "ryo"} t0)))))

(deftest succession-starts-before-the-parent-stops
  (let [o (lineage/organism {:family-name "awai" :given-name "ryo"} t0)
        at (fn [frac] (+ t0 (long (* frac (:organism/lifetime-ms o)))))]
    (is (= :active-life (lineage/life-phase o (at 0.5))))
    (is (= :succession-planning (lineage/life-phase o (at 0.8))))
    (is (= :handover (lineage/life-phase o (at 0.95))))
    (is (= :expired (lineage/life-phase o (at 1.0))))
    (is (lineage/expired? o (at 1.1)))))

(deftest vitality-cannot-be-compensated-away
  (testing "geometric mean: perfect everything else, zero human agency ≈ zero"
    (is (< (lineage/lineage-vitality
            {:human-agency 0.0 :relational-trust 1.0 :inheritable-learning 1.0
             :future-optionality 1.0 :succession-integrity 1.0})
           0.01)))
  (testing "an arithmetic mean would have scored that 0.8 — the whole point"
    (is (= 1.0 (lineage/lineage-vitality
                (zipmap lineage/wellbeing-dimensions (repeat 1.0)))))))

(def healthy (zipmap lineage/wellbeing-dimensions (repeat 0.9)))

(deftest reproduction-always-needs-signed-consent
  (let [o (lineage/organism {:family-name "awai" :given-name "ryo"} t0)
        gate (fn [opts] (lineage/action-gate (merge {:individual o :now-ms t0
                                                     :wellbecoming healthy
                                                     :action :reproduce} opts)))]
    (testing "no vitality score substitutes for consent"
      (is (= :approval-required (gate {})))
      (is (= :approval-required (gate {:human-consent? true :consent-signature ""})))
      (is (= :allowed (gate {:human-consent? true :consent-signature "sig"}))))
    (testing "low human agency pauses work before anything else is considered"
      (is (= :repair-relationship
             (lineage/action-gate {:individual o :now-ms t0 :action :work
                                   :wellbecoming (assoc healthy :human-agency 0.2)}))))))

(deftest only-consented-provenanced-memes-are-inherited
  (let [memes [{:meme/inheritable? true :meme/provenance ["run-1"] :meme/consent :scoped}
               {:meme/inheritable? true :meme/provenance [] :meme/consent :scoped}
               {:meme/inheritable? false :meme/provenance ["run-2"] :meme/consent :scoped}
               {:meme/inheritable? true :meme/provenance ["run-3"]}]]
    (is (= 1 (count (lineage/inheritable-memes memes))))))

(deftest succession-blocked-without-consent-produces-no-child
  (let [parent (lineage/organism {:family-name "awai" :given-name "ryo"} t0)
        blocked (lineage/succession-plan {:parent parent :child-name "ryo2"
                                          :now-ms t0 :wellbecoming healthy
                                          :memes []})
        approved (lineage/succession-plan {:parent parent :child-name "ryo2"
                                           :now-ms t0 :wellbecoming healthy
                                           :memes [] :human-consent? true
                                           :consent-signature "sig"})]
    (is (= :blocked (:succession/status blocked)))
    (is (nil? (:succession/child blocked)))
    (is (= :approved (:succession/status approved)))
    (is (= 2 (get-in approved [:succession/child :organism/generation])))
    (is (= (:organism/id parent) (get-in approved [:succession/child :organism/parent])))))

;; ──────────────────────────── policy ────────────────────────────

(deftest an-unlisted-capability-is-blocked
  (testing "defaulting unreviewed capabilities open is how an AO gains
            powers by omission"
    (is (= :blocked (policy/decide {} :mail.send)))
    (is (not (policy/may-execute? {} :anything)))))

(deftest legacy-spellings-resolve-and-are-reported
  (let [p {:mail.inbound :self-executing
           :mail.send :propose
           :account.create :forbidden
           :prolific.read :autonomous}]
    (is (= :autonomous (policy/decide p :mail.inbound)))
    (is (= :approval-required (policy/decide p :mail.send)))
    (is (= :blocked (policy/decide p :account.create)))
    (testing "the drafted fourth vocabulary is reported so it can die"
      (is (= 3 (count (policy/deprecated-spellings p))))
      (is (empty? (policy/deprecated-spellings {:a :autonomous}))))))

(deftest unknown-decisions-fail-closed
  (testing "a policy nobody can parse must not read as permission"
    (is (= :blocked (policy/decide {:x :whatever} :x)))
    (is (= :blocked (policy/normalize-decision nil)))))

(deftest strictest-wins-when-rules-overlap
  (is (= :blocked (policy/strictest-of [:autonomous :blocked])))
  (is (= :approval-required (policy/strictest-of [:autonomous :approval-required])))
  (is (= :approval-required (policy/strictest-of [:self-executing :propose])))
  (is (= :blocked (policy/strictest-of []))))

(deftest voice-required-is-not-quietly-autonomous
  (let [p {:x :voice-required}]
    (is (not (policy/may-execute? p :x)))
    (is (not (policy/needs-human? p :x)))))

(deftest validate-reports-every-problem-at-once
  (let [r (policy/validate-policy {:a :nonsense :b :also-nonsense :c :autonomous})]
    (is (not (:ok? r)))
    (is (= 2 (count (:problems r)))))
  (is (:ok? (policy/validate-policy {:a :autonomous :b :propose}))))
