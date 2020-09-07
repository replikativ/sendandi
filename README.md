# sendandi

From icelandic "sendandi" which means "dispatcher".

An abstraction above datalog databases for functions like connections, transactions, and queries. Only Datomic and Datahike is supported.

## Usage

For an example with datahike include latest version in your dependencies and fire up a REPL:

```clojure
(require '[sendandi.api :as s])
;; => nil

;; define configuration
(def cfg {:store              {:backend :mem
                               :id      "sendandi"}
          :keep-history?      true
          :schema-flexibility :write
          :name               "Sendandi"
          :initial-tx            [{:db/ident       :name
                                :db/valueType   :db.type/string
                                :db/cardinality :db.cardinality/one}]})
;; => #'sendandi.api/cfg

;; create a client
(def client (s/datahike-client cfg))
;; => #'sendandi.api/client

;; create a new database
(s/create-database client {})
;; => nil

;; connect to the database
(def conn (s/connect client {}))
;; => #'sendandi.api/conn

;; add new data
(s/transact conn [{:name "Otto"} {:name "Eva"}])
;; =>
;; {:db-before #sendandi.api.DatahikeDb{:state #datahike/DB{:schema {:db/ident #:db{:unique :db.unique/identity},
;;                                                                   :name #:db{:ident :name,
;;                                                                              :valueType :db.type/string,
;;                                                                              :cardinality :db.cardinality/one},
;;                                                                  1 :name}}},
;; :db-after #sendandi.api.DatahikeDb{:state #datahike/DB{:schema {:db/ident #:db{:unique :db.unique/identity},
;;                                                                 :name #:db{:ident :name,
;;                                                                            :valueType :db.type/string,
;;                                                                            :cardinality :db.cardinality/one},
;;                                                                 1 :name}}},
;; :tx-data [#datahike/Datom[536870914 :db/txInstant #inst"2020-09-07T09:24:58.534-00:00" 536870914 true]
;;           #datahike/Datom[2 :name "Otto" 536870914 true]
;;           #datahike/Datom[3 :name "Eva" 536870914 true]],
;; :tempids #:db{:current-tx 536870914}}

;; query the data
(s/q '[:find ?e ?n
       :where
       [?e :name ?n]]
     (s/db conn))
;; => #{[3 "Eva"] [2 "Otto"]}

;; pull an entity
(s/pull (s/db conn) '[*] 3)
;; => {:db/id 3, :name "Eva"}

;; get all datoms from the EAVT index
(s/datoms (s/db conn) {:index :eavt
                       :components nil})
;; =>
;; (#datahike/Datom[1 :db/cardinality :db.cardinality/one 536870913 true]
;;  #datahike/Datom[1 :db/ident :name 536870913 true]
;;  #datahike/Datom[1 :db/valueType :db.type/string 536870913 true]
;;  #datahike/Datom[2 :name "Otto" 536870914 true]
;;  #datahike/Datom[3 :name "Eva" 536870914 true]
;;  #datahike/Datom[536870913 :db/txInstant #inst"2020-09-07T09:24:51.655-00:00" 536870913 true]
;;  #datahike/Datom[536870914 :db/txInstant #inst"2020-09-07T09:24:58.534-00:00" 536870914 true])

(def date (java.util.Date.))
;; => #'sendandi.api/date

;; retract some data
(s/transact conn [[:db/retract 2 :name "Otto"]])
;; =>
;; {:db-before #sendandi.api.DatahikeDb{:state #datahike/DB{:schema {:db/ident #:db{:unique :db.unique/identity},
;;                                                                   :name #:db{:ident :name,
;;                                                                              :valueType :db.type/string,
;;                                                                              :cardinality :db.cardinality/one},
;;                                                                   1 :name}}},
;;  :db-after #sendandi.api.DatahikeDb{:state #datahike/DB{:schema {:db/ident #:db{:unique :db.unique/identity},
;;                                                                  :name #:db{:ident :name,
;;                                                                             :valueType :db.type/string,
;;                                                                             :cardinality :db.cardinality/one},
;;                                                                  1 :name}}},
;;  :tx-data [#datahike/Datom[536870915 :db/txInstant #inst"2020-09-07T09:25:09.784-00:00" 536870915 true]
;;            #datahike/Datom[2 :name "Otto" 536870915 false]],
;;  :tempids #:db{:current-tx 536870915}}

;; check whether it still exists
(s/q '[:find ?e ?n
       :where
       [?e :name ?n]]
     (s/db conn))
;; => #{[3 "Eva"]}

;; check former database for the data
(s/q '[:find ?e ?n
       :where
       [?e :name ?n]]
     (s/as-of (s/db conn) date))
;; => #{[3 "Eva"] [2 "Otto"]}

;; query history for changes in the entity
(s/q '[:find ?e ?n ?tx ?added
        :where
       [?e :name ?n ?tx ?added]]
     (s/history (s/db conn)))
;; => #{[2 "Otto" 536870915 false] [2 "Otto" 536870914 true] [3 "Eva" 536870914 true]}

;; remove database again
(s/delete-database client {})
;; => {}
```

## License

Copyright © 2020 Konrad Kühne

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
