(ns standby-api.routes.auth
  (:require [compojure.core :as cpj]
            [standby-api.external-api.stytch :as stytch]
            [standby-api.data.users :as users]
            [standby-api.middleware.stytch-store :as stytch-store]
            [ring.util.http-response :as response]))

(defn set-session [session-token csrf-token response]
  ;; TODO only on local
  ;; this is needed because we can only set use http for localhost in stytch, and I haven't setup
  ;; https locally yet
  ;; (println "for browser:" "relevance-session" session_token ".buyersphere-local.com")
  ;; (println "for postman:" (format "relevance-session%s" session_token))
  (-> response 
      (assoc :session session-token)
      (assoc :session-csrf-token csrf-token)))

(def ^:private gmail-send-scope "https://www.googleapis.com/auth/gmail.send")
(def ^:private gmail-modify-scope-scope "https://www.googleapis.com/auth/gmail.modify")

(defn- process-stytch-token
  "processes a stytch session token to return the set of values we care about.
   specifically a returns 
   
   {:first-name 
   :last-name 
   :email 
   :image
   :session-token
   :has-send-scope
   :has-gmail-modify-scope
   :refresh-token
   :provider-values}
   
   Provider values is the raw google response"
  [{session-token :session-token
    {{:keys [first-name last-name]} :name
     [{:keys [email]}] :emails
     [{:keys [profile-picture-url]}] :providers} :user
    {:keys [refresh-token scopes] :as provider-values} :provider-values}]
  (let [has-send-scope (->> scopes
                            (filter #(= % gmail-send-scope))
                            first
                            boolean)
        has-gmail-modify-scope (->> scopes
                                    (filter #(= % gmail-modify-scope-scope))
                                    first
                                    boolean)]
    {:first-name first-name
     :last-name last-name
     :email email
     :image profile-picture-url
     :session-token session-token
     :has-send-scope has-send-scope
     :has-gmail-modify-scope has-gmail-modify-scope
     :refresh-token refresh-token
     :provider-values provider-values}))

(defn- oauth-login [db stytch-config front-end-base-url token]
  (let [stytch-response (stytch/authenticate-oauth stytch-config token)
        {:keys [session-token email] :as stytch-values} (process-stytch-token stytch-response)]
    (if session-token
      (do
        (users/update-user-from-stytch db email stytch-values)
        (let [user (stytch/authenticate-session stytch-config session-token)
              {:keys [csrf-token]} (stytch-store/cache-stytch-login db session-token user)]
          (set-session session-token csrf-token (response/found front-end-base-url))))
      (response/found (str front-end-base-url "/login")))))

(def GET-login
  (cpj/GET "/v0.1/login" [stytch-token-type token :as {:keys [db config] :as req}]
    (condp = stytch-token-type
      "oauth" (oauth-login
               db
               (:stytch config)
               (-> config :front-end :base-url)
               token)
      (response/bad-request "Unknown stytch_token_type"))))

(defn- oauth-signup [db stytch-config front-end-base-url token]
  (let [stytch-response (stytch/authenticate-oauth stytch-config token)
        {:keys [session-token email] :as stytch-values} (process-stytch-token stytch-response)]
    (if session-token
      (do
        (users/create-user db email stytch-values)
        (let [user (stytch/authenticate-session stytch-config session-token)
              {:keys [csrf-token]} (stytch-store/cache-stytch-login db session-token user)]
          (set-session session-token csrf-token (response/found front-end-base-url))))
      (response/found (str front-end-base-url "/login")))))

(def GET-signup
  (cpj/GET "/v0.1/signup" [stytch-token-type token :as {:keys [db config] :as req}]
    (condp = stytch-token-type
      "oauth" (oauth-signup
               db
               (:stytch config)
               (-> config :front-end :base-url)
               token)
      (response/bad-request "Unknown stytch_token_type"))))
