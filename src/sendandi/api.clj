(ns sendandi.api
  (:refer-clojure :exclude [sync])
  (:require [datomic.client.api :as dc]
            [datahike.api :as dh]))

(defn not-supported []
  (throw (ex-info "Not supported yet." {:error/cause :not-supported})))

(defprotocol Historian
  (db-stats [db])
  (history [db])
  (as-of [db time-point])
  (since [db time-point])
  (with [db arg-map]))

(defprotocol Searcher
  (datoms [db arg-map])
  (pull [db arg-map] [db selector eid])
  (index-range [db arg-map]))

(defrecord DatahikeDb [state]
  Historian
  (db-stats [db] (not-supported))
  (history [db] (map->DatahikeDb {:state (dh/history state)}))
  (as-of [db time-point] (map->DatahikeDb {:state (dh/as-of state time-point)}))
  (since [db time-point] (map->DatahikeDb {:state (dh/since state time-point)}))
  (with [db {:keys [tx-data]}]
    (dh/with state tx-data))

  Searcher
  (datoms [db {:keys [index components]}]
    (dh/datoms state index components))
  (pull [db {:keys [selector eid]}]
    (dh/pull state selector eid))
  (pull [db selector eid]
    (dh/pull state selector eid))
  (index-range [db arg-map] (not-supported)))

(defrecord DatomicDb [state]
  Historian
  (db-stats [db] (not-supported))
  (history [db] (map->DatomicDb {:state (dc/history state)}))
  (as-of [db time-point] (map->DatomicDb {:state (dc/as-of state time-point)}))
  (since [db time-point] (map->DatomicDb {:state (dc/since state time-point)}))
  (with [db arg-map] (dc/with state arg-map))

  Searcher
  (datoms [db arg-map] (dc/datoms db arg-map))
  (pull [db arg-map] (dc/pull state arg-map))
  (pull [db selector eid] (dc/pull state selector eid))
  (index-range [db arg-map] (not-supported)))

(defprotocol Transactor
  (transact [conn arg-map])
  (release [conn])
  (db [conn])
  (with-db [conn])
  (sync [conn time-point])
  (tx-range [conn arg-map]))

(defrecord DatahikeConnection [state]
  Transactor
  (transact [conn arg-map]
    (let [{:keys [db-after db-before tx-data tempids] :as tx-result} (dh/transact state arg-map)]
      {:db-before (map->DatahikeDb {:state db-before})
       :db-after (map->DatahikeDb {:state db-after})
       :tx-data tx-data
       :tempids tempids}))
  (release [conn] (dh/release state))
  (db [conn] (map->DatahikeDb {:state @state}))
  (with-db [conn] @state)
  (sync [conn time-point] (not-supported))
  (tx-range [conn arg-map] (not-supported)))

(defrecord DatomicConnection [state]
  Transactor
  (transact [conn arg-map]
    (let [{:keys [db-after db-before tx-data tempids] :as tx-result} (dc/transact state arg-map)]
      {:db-before (map->DatomicDb {:state db-before})
       :db-after (map->DatomicDb {:state db-after})
       :tx-data tx-data
       :tempids tempids}))
  (release [conn])
  (db [conn] (map->DatomicDb {:state (dc/db state)}))
  (with-db [conn] (dc/with-db state))
  (sync [conn time-point] (not-supported))
  (tx-range [conn arg-map] (not-supported)))

(defprotocol Connector
  (connect [client arg-map]))

(defprotocol Creator
  (administer-system [client])
  (list-databases [client arg-map])
  (create-database [client arg-map])
  (delete-database  [client arg-map]))

(defrecord DatahikeClient [state]
  Connector
  (connect [client arg-map]
    (map->DatahikeConnection {:state (dh/connect state)}))

  Creator
  (administer-system [client] (not-supported))
  (list-databases [client arg-map] (not-supported))
  (create-database [client arg-map]
    (dh/create-database state))
  (delete-database [client arg-map]
    (dh/delete-database state)))

(defrecord DatomicClient [state]
  Connector
  (connect [client arg-map]
    (map->DatomicConnection {:state (dc/connect state arg-map)}))

  Creator
  (administer-system [client] (not-supported))
  (list-databases [client arg-map] (not-supported))
  (create-database [client arg-map]
    (dc/create-database state arg-map))
  (delete-database [client arg-map]
    (dc/delete-database state arg-map)))

(defn datahike-client [config]
  (map->DatahikeClient {:state config}))

(defn datomic-client [config]
  (map->DatomicClient {:state (dc/client config)}))

(defmulti -q-map (fn [{:keys [args] :as arg-map}] (-> args first class)))

(defmethod -q-map DatahikeDb [arg-map]
  (dh/q (update-in arg-map [:args] (fn [old] (mapv (fn [arg]
                                                     (if (= DatahikeDb (type arg))
                                                       (:state arg)
                                                       arg)) old)))))

(defmethod -q-map DatomicDb [arg-map]
  (dc/q (update-in arg-map [:args] (fn [old] (mapv (fn [arg]
                                                     (if (= DatomicDb (type arg))
                                                       (:state arg)
                                                       arg)) old)))))

(defmulti -q (fn [query & args] (-> args first class)))

(defmethod -q DatahikeDb [query & args]
  (apply dh/q query (mapv (fn [arg]
                            (if (= DatahikeDb (type arg))
                              (:state arg)
                              arg))
                          args)))

(defmethod -q DatomicDb [query & args]
  (apply dc/q query (mapv (fn [arg]
                            (if (= DatomicDb (type arg))
                              (:state arg)
                              arg)) args) (rest args)))

(defn q
  ([arg-map] (-q-map arg-map))
  ([query & args] (apply -q query args)))

