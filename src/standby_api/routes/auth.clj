(ns standby-api.routes.auth
  (:require [compojure.core :as cpj]
            [lambdaisland.uri :as uri]
            [standby-api.external-api.stytch :as stytch]
            [ring.util.http-response :as response]))

(defn set-session [session_token response]
  ;; TODO only on local
  ;; this is needed because we can only set use http for localhost in stytch, and I haven't setup
  ;; https locally yet
  (println "for browser:" "buyersphere-session" session_token ".buyersphere-local.com")
  (println "for postman:" (format "buyersphere-session=%s" session_token))
  (assoc response :session session_token))

(defn- oauth-login [db stytch-config front-end-base-url token]
  (let [{session-token :session-token
         {{:keys [first-name last-name]} :name
          [{:keys [email]}] :emails
          [{:keys [profile-picture-url]}] :providers} :user}
        (stytch/authenticate-oauth stytch-config token)]
    (if session-token
      (do
        ;; TODO
        ;; (d-users/update-user-from-stytch db email_address name (-> oauth_registrations first :profile_picture_url))
        (set-session session-token (response/found front-end-base-url)))
      (response/found (str front-end-base-url "/login")))))

(def GET-login
  (cpj/GET "/v0.1/login" [stytch-token-type token :as {:keys [db config]}]
    (condp = stytch-token-type
      "oauth" (oauth-login
               db
               (:stytch config)
               (-> config :front-end :base-url)
               token)
      (response/bad-request "Unknown stytch_token_type"))))

(defn- oauth-signup [db stytch-config front-end-base-url token]
  (let [{session-token :session-token
         {{:keys [first-name last-name]} :name
          [{:keys [email]}] :emails
          [{:keys [profile-picture-url]}] :providers} :user}
        (stytch/authenticate-oauth stytch-config token)]
    (println "first" first-name)
    (println "email" email)
    (println "pic" profile-picture-url  )
    (if session-token
      (do
        ;; TODO
        ;; (d-users/update-user-from-stytch db email_address name (-> oauth_registrations first :profile_picture_url))
        (set-session session-token (response/found front-end-base-url)))
      (response/found (str front-end-base-url "/login")))))

(def GET-signup
  (cpj/GET "/v0.1/signup" [stytch-token-type token :as {:keys [db config]}]
    (condp = stytch-token-type
      "oauth" (oauth-signup
               db
               (:stytch config)
               (-> config :front-end :base-url)
               token)
      (response/bad-request "Unknown stytch_token_type"))))

;; https://test.stytch.com/v1/public/oauth/google/start?public_token=public-token-test-149e4c38-50fd-4eb3-9b34-0c2992e73a07