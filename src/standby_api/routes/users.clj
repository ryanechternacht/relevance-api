(ns standby-api.routes.users
   (:require [compojure.core :as cpj]
             [ring.util.http-response :as response]
             [standby-api.data.gmail-sync :as gmail-sync]
             [standby-api.data.users :as users]
             [standby-api.middleware.csrf :as csrf]))

 (def GET-users-me
   (cpj/GET "/v0.1/users/me" {user :user}
     (if user
       (response/ok user)
       (response/unauthorized))))

 (def GET-users-me-gmail-sync
   (cpj/GET "/v0.1/users/me/gmail-sync" {:keys [user db]}
     (if user
       (response/ok (gmail-sync/get-for-user db (:email user)))
       (response/unauthorized))))

 (def GET-users-shortcode
   (cpj/GET "/v0.1/users/shortcode/:shortcode" [shortcode :as {:keys [db]}]
     (response/ok (users/get-by-shortcode db shortcode))))

 (def PATCH-users-me
   (cpj/PATCH "/v0.1/users/me" {:keys [db user body] :as req}
     (cond
       (not user) (response/unauthorized)
       (not (csrf/valid-csrf? req)) (csrf/csrf-failure-response)
       :else (users/update-user db (:email user) body))))

(def PATCH-users-me-public-link
  (cpj/PATCH "/v0.1/users/me/public-link" {:keys [db user body] :as req}
    (cond
      (not user) (response/unauthorized)
      (not (csrf/valid-csrf? req)) (csrf/csrf-failure-response)
      :else (let [link (:public-link body)]
              (if-let [error (users/check-public-link link)]
                (response/bad-request {:error error})
                (if-let [result (users/update-user-public-link db
                                                               (:email user)
                                                               link)]
                  (response/ok result)
                  (response/bad-request {:error "public link in user by another user"})))))))

(def PATCH-users-me-gmail-sync
  (cpj/PATCH "/v0.1/users/me/gmail-sync" {:keys [db user body] :as req}
    (cond
      (not user) (response/unauthorized)
      (not (csrf/valid-csrf? req)) (csrf/csrf-failure-response)
      :else (do
              (when (:is-enabled body)
                (gmail-sync/setup-auth db (:email user)))
              (response/ok (gmail-sync/update-gmail-sync db (:email user) body))))))

;; This isn't correctly clearly frontend cookies, but w/e
;; the real issue is that creates "failures" on our backend
(def GET-users-me-logout
  (cpj/GET "/v0.1/users/me/logout" {:keys [user]}
    (if user
      (assoc (response/found "https://www.relevance.to") :session nil)
      (response/unauthorized))))
