(ns standby-api.routes
  (:require [compojure.core :as cpj]
            [ring.util.http-response :as response]
            [standby-api.routes.auth :as auth]
            [standby-api.routes.outreach :as outreach]
            [standby-api.routes.users :as users]))

(def GET-root-healthz
  (cpj/GET "/" []
    (response/ok "I'm here")))

(def get-404
  (cpj/GET "*" []
    (response/not-found)))

(def post-404
  (cpj/POST "*" []
    (response/not-found)))

(def patch-404
  (cpj/PATCH "*" []
    (response/not-found)))

(def put-404
  (cpj/PUT "*" []
    (response/not-found)))

(def delete-404
  (cpj/DELETE "*" []
    (response/not-found)))

(cpj/defroutes routes
  GET-root-healthz
  ;; auth/GET-gmail-approval
  auth/GET-login
  auth/GET-signup
  outreach/GET-outreach
  outreach/PATCH-outreach
  outreach/POST-outreach-reply
  outreach/POST-outreach
  outreach/GET-outreach-uuid
  users/GET-users-me
  users/GET-users-shortcode
  users/PATCH-users-me
  users/PATCH-users-me-public-link
  get-404
  post-404
  patch-404
  put-404
  delete-404)
