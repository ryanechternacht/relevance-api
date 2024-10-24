(ns standby-api.middleware.stytch-store
  (:require [honey.sql.helpers :as h]
            [ring.middleware.session.store :as rs]
            [standby-api.db :as db]
            [standby-api.external-api.stytch :as stytch]
            [crypto.random :as random]))

(defn- check-db-for-cached-session [db session-token]
  (let [query (-> (h/select :stytch_member_json :csrf_token)
                  (h/from :session_cache)
                  (h/where [:= :stytch_session_id session-token]
                           [:>= :valid_until :current_timestamp]))
        {:keys [stytch-member-json csrf-token]} (->> query
                                                     (db/->>execute db)
                                                     first)]
    (when stytch-member-json
      {:member stytch-member-json
       :csrf-token csrf-token})))

(defn cache-stytch-login [db session-token stytch-member]
  (let [insert-query (-> (h/insert-into :session_cache)
                         (h/columns :stytch_session_id :stytch_member_json :valid_until :csrf_token)
                         (h/values [[session-token [:lift stytch-member] [:raw (str "NOW() + INTERVAL '" 30 " MINUTES'")] (random/base64 60)]])
                         (h/on-conflict :stytch_session_id)
                         (h/do-update-set :stytch_member_json :valid_until))
        _ (db/execute db insert-query)
        get-query (-> (h/select :stytch_member_json :csrf_token)
                      (h/from :session_cache)
                      (h/where [:= :stytch_session_id session-token]))
        {:keys [stytch-member-json csrf-token]} (->> get-query
                                                     (db/->>execute db)
                                                     first)]
    {:member stytch-member-json
     :csrf-token csrf-token}))

(defn- remove-session [db session-token]
  (let [query (-> (h/delete-from :session_cache)
                  (h/where [:= :stytch_session_id session-token]))]
    (->> query
         (db/->>execute db))))

(comment
  (cache-stytch-login db/local-db "abc123" {:a 1 :b 2 :c 3})
  (check-db-for-cached-session db/local-db "abc123")
  ;
  )

(deftype StytchStore [stytch-config db]
  rs/SessionStore
  (read-session
    [_ session-token]
    ;; Check if a valid cached version exists in the db. if not, validate with stytch and
    ;; update what we have in the db
    (when session-token
      (if-let [session-data (check-db-for-cached-session db session-token)]
        session-data
        (when-let [member (stytch/authenticate-session stytch-config session-token)]
          (cache-stytch-login db session-token member)))))
  (write-session
    [_ _ value]
    value)
  (delete-session
    [_ session-token]
    (stytch/revoke-session stytch-config session-token)
    (remove-session db session-token)
    nil))

(defn stytch-store
  "creates a store backed by stytch.com identity service"
  [stytch-config db]
  (StytchStore. stytch-config db))
