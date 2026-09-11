(ns ao.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [ao.identity :as ident]
            [ao.lineage :as lineage]
            [ao.evolution :as evo]))

(def t0 1785000000000)

;; ─────────────────────────── identity ───────────────────────────

(deftest an-ao-name-is-derived-not-assigned
  (is (= "ao:github:network-awai/person-awai-ryo"
         (ident/ao-id "network-awai" "person-awai-ryo")))
  (is (= {:ao/org "network-awai" :ao/repo "person-awai-ryo"}
         (ident/parse-id "ao:github:network-awai/person-awai-ryo")))
  (testing "an id that is not derivable from a repo slug is not an AO id"
    (is (not (ident/valid-id? "ao:github:no-repo")))
    (is (not (ident/valid-id? "person-awai-ryo")))
    (is (thrown? #?(:clj Exception :cljs js/Error) (ident/ao-id "org" "")))))

(deftest archiving-makes-an-ao-dormant-never-deleted
  (testing "an archived repository still has history someone may answer for"
    (let [live (ident/organism {:org "o" :repo "r"})
          arch (ident/organism {:org "o" :repo "r" :archived? true})]
      (is (= :active (:ao/state live)))
      (is (ident/dormant? arch))
      (is (= (:ao/id live) (:ao/id arch)) "identity survives archival")
      (is (not (contains? ident/states :deleted))))))

(deftest family-membership-is-a-rule-over-an-inventory
  (testing "a curated list drifts the moment someone forgets to update it"
    (let [repos [{:org "network-awai" :repo "a"}
                 {:org "network-awai" :repo "b" :archived? true}
                 {:org "kotoba-lang" :repo "ao"}]
          fam (ident/family-members repos {:org "network-awai"})]
      (is (= 2 (count fam)))
      (is (= #{"a" "b"} (set (map :ao/repo fam))))
      (testing "dormant members stay members"
        (is (some ident/dormant? fam))))))

;; ─────────────────────────── evolution ───────────────────────────

(def healthy-wb (zipmap lineage/wellbeing-dimensions (repeat 0.9)))

(defn inc-at [now] (lineage/organism {:family-name "awai" :given-name "hikari"} now))

(deftest merge-authority-is-separate-from-push
  (testing "merging is what makes a change official; an AO that can merge its
            own work has no reviewer left"
    (let [pusher {:ao/write-scope :push}]
      (is (evo/may-write? pusher :commit))
      (is (evo/may-write? pusher :push))
      (is (not (evo/may-write? pusher :merge))))))

(deftest an-expired-incarnation-cannot-argue-past-the-lease
  (let [i (inc-at t0)
        late (+ t0 (* 31 lineage/day-ms))]
    (is (= :expired (evo/gate {:ao {:ao/write-scope :merge}
                               :incarnation i :now-ms late
                               :wellbecoming healthy-wb
                               :change {:change/scope :commit}})))))

(deftest self-modification-requires-write-scope
  (is (= :insufficient-write-scope
         (evo/gate {:ao {:ao/write-scope :commit}
                    :incarnation (inc-at t0) :now-ms t0
                    :wellbecoming healthy-wb
                    :change {:change/scope :merge}}))))

(deftest editing-the-terms-of-its-own-supervision-needs-a-signed-human
  (let [base {:ao {:ao/write-scope :merge}
              :incarnation (inc-at t0) :now-ms t0
              :wellbecoming healthy-wb}]
    (testing "ordinary work proceeds"
      (is (= :allowed (evo/gate (assoc base :change {:change/scope :merge})))))
    (testing "a governing change does not"
      (is (= :approval-required
             (evo/gate (assoc base :change {:change/scope :merge
                                            :change/governing? true}))))
      (is (= :allowed
             (evo/gate (assoc base :change {:change/scope :merge
                                            :change/governing? true}
                              :human-consent? true
                              :consent-signature "sig")))))))

(deftest governing-paths-are-recognised
  (is (evo/governing-change? ["actor.edn"]))
  (is (evo/governing-change? ["src/x.cljc" "docs/policy.md"]))
  (is (evo/governing-change? ["config/capabilities.edn"]))
  (is (not (evo/governing-change? ["README.md" "src/util.cljc"]))))

(deftest low-human-agency-stops-self-modification-too
  (is (= :repair-relationship
         (evo/gate {:ao {:ao/write-scope :merge}
                    :incarnation (inc-at t0) :now-ms t0
                    :wellbecoming (assoc healthy-wb :human-agency 0.2)
                    :change {:change/scope :commit}}))))

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
