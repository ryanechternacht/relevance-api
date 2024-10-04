(ns standby-api.routes.users
  (:require [compojure.core :as cpj]
            [ring.util.http-response :as response]
            [standby-api.data.users :as users]))

(def GET-users-me
  (cpj/GET "/v0.1/users/me" {user :user}
    (if user
      (response/ok user)
      (response/unauthorized))))

(def GET-users-shortcode
  (cpj/GET "/v0.1/users/shortcode/:shortcode" [shortcode :as {:keys [db]}]
    (response/ok (users/get-by-shortcode db shortcode))))

(def PATCH-users-me
  (cpj/PATCH "/v0.1/users/me" {:keys [db user body]}
    (if user
      (response/ok (users/update-user db (:email user) body))
      (response/unauthorized))))
