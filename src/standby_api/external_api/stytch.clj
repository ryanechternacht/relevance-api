(ns standby-api.external-api.stytch
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [standby-api.utilities :as u]))

(def ^:private default-session-timeout-minutes (* 30 24 60))

(defn- make-stytch-call [url project secret body]
  (http/post url
             {:content-type :json
              :basic-auth [project secret]
              :body (json/generate-string body)
              :accept :json
              :as :json}))

(defn authenticate-session
  "Authenticates a session with stytch. Returns the logged in
   user or nil if the session isn't valid"
  [{:keys [base-url project secret]}
   session-token]
  (try
    (-> (make-stytch-call (u/make-link base-url "sessions/authenticate")
                          project
                          secret
                          {:session_token session-token
                           :session_duration_minutes default-session-timeout-minutes})
        :body
        :member)
    (catch Exception _
      ;; (println "authenticate-session exception" _)
      nil)))

;; (defn authenticate-magic-link
;;   "Authenticates the magic link login attempt with stytch.
;;    Returns the session identifier or nil if the magic link login isn't valid."
;;   [{:keys [base-url project secret]}
;;    magic-link-token]
;;   (try
;;     (-> (make-stytch-call (u/make-link base-url "magic_links/authenticate")
;;                           project
;;                           secret
;;                           {:magic_links_token magic-link-token
;;                            :session_duration_minutes default-session-timeout-minutes})
;;         :body)
;;     (catch Exception e
;;       (println "authenticate-magic-link exception" e)
;;       nil)))

;; TODO pull values out of this that are reasonable (besides just session_token)
;; these should have the username, profile pic, etc
(defn authenticate-oauth
  "Authenticates the oauth login attempt with stytch.
   Returns the session identifier or nil if the oauth login isn't valid."
  [{:keys [base-url project secret]}
   auth-token]
  (try
    (-> (make-stytch-call (u/make-link base-url "oauth/authenticate")
                          project
                          secret
                          {:token auth-token
                           :session_duration_minutes default-session-timeout-minutes})
        :body
        u/kebab-case)
    (catch Exception e
      (println "authenticate-oauth exception" e)
      nil)))

;; (defn send-magic-link-email
;;   "Sends the user a magic-email-link. Returns a truthy value if the
;;    email was sent and a falsey value if it wasn't"
;;   [{:keys [base-url project secret redirect-url]}
;;    stytch-organization-id user-email]
;;   (try
;;     (make-stytch-call (u/make-link base-url "magic_links/email/login_or_signup")
;;                       project
;;                       secret
;;                       {:email_address user-email
;;                        :organization_id stytch-organization-id
;;                        :login_redirect_url redirect-url})
;;     (catch Exception e
;;       (println "send-magic-link-email exception" e)
;;       nil)))

;; (defn create-user
;;   "Creates a user in the given organization. Throws if the user
;;    could not be created in stytch"
;;   [{:keys [base-url project secret]}
;;    stytch-organization-id user-email name]
;;   (-> (make-stytch-call (u/make-link base-url
;;                                      (str "organizations/"
;;                                           stytch-organization-id
;;                                           "/members"))
;;                         project
;;                         secret
;;                         {:email_address user-email
;;                          :name name})
;;       :body
;;       :member
;;       :member_id))
