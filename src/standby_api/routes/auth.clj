(ns standby-api.routes.auth
  (:require ;; [clj-http.client :as http]
            [compojure.core :as cpj]
            [standby-api.external-api.stytch :as stytch]
            [standby-api.data.users :as users]
            ;; [standby-api.data.gmail-sync :as gmail-sync]
            [ring.util.http-response :as response]))

(defn set-session [session_token response]
  ;; TODO only on local
  ;; this is needed because we can only set use http for localhost in stytch, and I haven't setup
  ;; https locally yet
  ;; (println "for browser:" "relevance-session" session_token ".buyersphere-local.com")
  ;; (println "for postman:" (format "relevance-session%s" session_token))
  (assoc response :session session_token))

(def ^:private gmail-send-scope "https://www.googleapis.com/auth/gmail.send")

(defn- process-stytch-token
  "processes a stytch session token to return the set of values we care about.
   specifically a returns 
   
   {:first-name 
   :last-name 
   :email 
   :profile-picture-url
   :session-token
   :has-send-scope
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
                            boolean)]
    {:first-name first-name
     :last-name last-name
     :email email
     :profile-picture-url profile-picture-url
     :session-token session-token
     :has-send-scope has-send-scope
     :refresh-token refresh-token
     :provider-values provider-values}))

(defn- oauth-login [db stytch-config front-end-base-url token]
  (let [stytch-response (stytch/authenticate-oauth stytch-config token)
        {:keys [session-token email] :as stytch-values} (process-stytch-token stytch-response)]
    (if session-token
      (do
        (users/update-user-from-stytch db email stytch-values)
        (set-session session-token (response/found front-end-base-url)))
      (response/found (str front-end-base-url "/login")))))

(def GET-login
  (cpj/GET "/v0.1/login" [stytch-token-type token :as {:keys [db config] :as req}]
    (println "login" req)
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
        (set-session session-token (response/found front-end-base-url)))
      (response/found (str front-end-base-url "/login")))))

(def GET-signup
  (cpj/GET "/v0.1/signup" [stytch-token-type token :as {:keys [db config] :as req}]
    (println "signup" req)
    (condp = stytch-token-type
      "oauth" (oauth-signup
               db
               (:stytch config)
               (-> config :front-end :base-url)
               token)
      (response/bad-request "Unknown stytch_token_type"))))

;; (defn- prepare-email-auth [{:keys [client-id redirect-uri scopes]} db email]
;;   (let [{oauth-state :oauth_state} (gmail-sync/setup-auth db email)]
;;     (response/see-other
;;      (str "https://accounts.google.com/o/oauth2/v2/auth"
;;           "?client_id=" client-id
;;           "&redirect_uri=" redirect-uri
;;           "&response_type=code"
;;           "&scope=" scopes
;;           "&include_granted_scopes=true"
;;           "&access_type=offline"
;;           "&login_hint=" email
;;           "&prompt=consent" ;; forces a full flow which makes sure we also get a refresh token (and should help fix any auth issues)
;;           "&state=" oauth-state))))

;; (defn- complete-auth-flow [front-end-base-url {:keys [client-id client-secret redirect-uri]} db code state]
;;   (let [token (-> (http/post (str "https://oauth2.googleapis.com/token"
;;                                   "?client_id=" client-id
;;                                   "&client_secret=" client-secret
;;                                   "&code=" code
;;                                   "&grant_type=authorization_code"
;;                                   "&redirect_uri=" redirect-uri)
;;                             {:accept :json
;;                               :as :json})
;;                   :body)
;;         {user-email :user_email} (gmail-sync/save-token db state token)]
;;     (users/update-user db user-email {:mail-sync-status "ready"})
;;     (response/see-other (str front-end-base-url "/app/settings"))))

;; (def GET-gmail-approval
;;   (cpj/GET "/v0.1/gmail-approval" [code state :as {:keys [db config user]}]
;;     (let [google-config (:google-api config)
;;           front-end-base-url (-> config :front-end :base-url)]
;;       (if (not code)
;;         (prepare-email-auth google-config db (:email user))
;;         (complete-auth-flow front-end-base-url google-config db code state)))))
