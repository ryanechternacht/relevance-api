(ns standby-api.middleware.stytch-store
  (:require [honey.sql.helpers :as h]
            [ring.middleware.session.store :as rs]
            [standby-api.db :as db]
            [standby-api.external-api.stytch :as stytch]))

(defn- check-db-for-cached-session [db session-token]
  (-> (h/select :stytch_member_json)
      (h/from :session_cache)
      (h/where [:= :stytch_session_id session-token]
               [:>= :valid_until :current_timestamp])
      (db/->execute db)
      first
      :stytch_member_json))

(defn- cache-stytch-login [db session-token stytch-member]
  (-> (h/insert-into :session_cache)
      (h/columns :stytch_session_id :stytch_member_json :valid_until)
      (h/values [[session-token [:lift stytch-member] [:raw (str "NOW() + INTERVAL '" 30 " MINUTES'")]]])
      (h/on-conflict :stytch_session_id)
      (h/do-update-set :stytch_member_json :valid_until)
      (db/->execute db)))

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
    ;; TODO Not sure how this should interact with write-session, but I bet it should
    (when session-token
      (if-let [cached-member (check-db-for-cached-session db session-token)]
        cached-member
        (when-let [member (stytch/authenticate-session stytch-config session-token)]
          (cache-stytch-login db session-token member)
          member))))
  (write-session
    [_ _ value]
    value)
  (delete-session
    [_ _]
  ;;  TODO?
    nil))

(defn stytch-store
  "creates a store backed by stytch.com identity service"
  [stytch-config db]
  (StytchStore. stytch-config db))
