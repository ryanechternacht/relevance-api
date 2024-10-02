(ns standby-api.db
  (:require [cheshire.core :as json]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs])
  (:import [java.sql PreparedStatement]
           [org.postgresql.util PGobject]))

(def local-db {:dbtype "postgresql"
               :dbname "relevance"
               :host "127.0.0.1"
               :user "ryanechternacht"
               :password nil
               :ssl false})

(defn ->format
  "formats and returns the query for use in -> threading macros.
   NOTE: this is only for testing"
  ([query] (->format query nil))
  ([query _]
   (sql/format query {:inline true})))

(defn ->>format
  "formats and returns the query for use in ->> threading macros.
   NOTE: this is only for testing"
  ([query] (->format nil query))
  ([_ query]
   (sql/format query)))

(defn execute [db query]
  (jdbc/execute! db (sql/format query)
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defn ->execute
  "query comes first for use in -> threading macros"
  [query db]
  (execute db query))

(defn ->>execute
  "query comes last for use in ->> threading macros"
  [db query]
  (execute db query))

(def ^{:doc "A helper to generate timestamp based on suggestions in honeysql docs"}
  now
  (sql/call "STATEMENT_TIMESTAMP"))

(defn lift
  "wraps a value in a [:lift] statement for honeysql handling of jsonb"
  [x]
  [:lift x])

;; Everything below handles converting clj maps to/from jsonb fields in postgres
;; It was all ripped shamelessly from the docs below:
;; https://cljdoc.org/d/seancorfield/next.jdbc/1.1.646/doc/getting-started/tips-tricks
(def ->json json/generate-string)
(def <-json #(json/parse-string % true))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (<-json value) {:pgtype type}))
      value)))

(set! *warn-on-reflection* true)

;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))
