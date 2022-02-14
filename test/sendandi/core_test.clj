(ns sendandi.core-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as timbre]
            [sendandi.api :as s]))

(timbre/set-level! :warn)

(defn datomic-cfg [db-name]
  {:server-type :dev-local
   :storage-dir :mem
   :name db-name
   :system "CI"})

(defn datahike-cfg [db-name]
  {:store {:backend :mem
           :id db-name}})


(deftest simple-tx-and-q-comparison
  (let [dh-cfg    (datahike-cfg "dh-tx-q")
        dh-client (s/datahike-client dh-cfg)
        _         (do
                    (s/delete-database dh-client {})
                    (s/create-database dh-client {}))
        dh-conn   (s/connect dh-client {})
        dt-cfg    (datomic-cfg "dt-tx-q")
        dt-client (s/datomic-client dt-cfg)
        dt-db-cfg {:db-name "dt-tx-q"}
        _         (do
                    (s/delete-database dt-client dt-db-cfg)
                    (s/create-database dt-client dt-db-cfg))
        dt-conn   (s/connect dt-client dt-db-cfg)
        db-schema {:tx-data [{:db/ident       :name
                              :db/valueType   :db.type/string
                              :db/unique      :db.unique/identity
                              :db/cardinality :db.cardinality/one}
                             {:db/ident       :age
                              :db/valueType   :db.type/long
                              :db/cardinality :db.cardinality/one}]}
        tx-data   {:tx-data [{:name "Otto"
                              :age  55}
                             {:name "Eva"
                              :age  44}]}
        q-1       {:query '[:find ?n ?a
                            :where
                            [?e :name ?n]
                            [?e :age ?a]]
                   :args  []}]
    (s/transact dh-conn db-schema)
    (s/transact dt-conn db-schema)
    (s/transact dh-conn tx-data)
    (s/transact dt-conn tx-data)

    (testing "datalog query"
      (is (= (into #{}
                   (s/q (assoc q-1 :args [(s/db dh-conn)])))
             (into #{}
                   (s/q (assoc q-1 :args [(s/db dt-conn)]))))))

    (testing "pull expression"
      (let [pull-expr '[:name :age]
            id        [:name "Otto"]]
        (is (= (s/pull (s/db dh-conn) pull-expr id)
               (s/pull (s/db dt-conn) pull-expr id)))))

    (testing "retraction"
      (let [date       (java.util.Date.)
            retract-tx {:tx-data [[:db/retractEntity [:name "Otto"]]]}]
        (s/transact dh-conn retract-tx)
        (s/transact dt-conn retract-tx)
        (testing "current database"
          (is (= (into #{}
                       (s/q (assoc q-1 :args [(s/db dh-conn)])))
                 (into #{}
                       (s/q (assoc q-1 :args [(s/db dt-conn)]))))))
        (testing "as-of database"
          (is (= (into #{}
                       (s/q (assoc q-1 :args [(s/as-of (s/db dh-conn) date)])))
                 (into #{}
                       (s/q (assoc q-1 :args [(s/as-of (s/db dt-conn) date)]))))))
        (testing "history database"
          (let [history-q {:query '[:find ?n ?a ?added
                                    :where
                                    [?e :name ?n _ ?added]
                                    [?e :age ?a]
                                    ]
                           :args  []}]
            (testing "as-of database"
              (is (= (into #{}
                           (s/q (assoc history-q :args [(s/history (s/db dh-conn))])))
                     (into #{}
                           (s/q (assoc history-q :args [(s/history (s/db dt-conn))]))))))))))))

