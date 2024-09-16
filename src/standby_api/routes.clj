(ns standby-api.routes
  (:require [compojure.core :as cpj]
            [ring.util.http-response :as response]))

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
  get-404
  post-404
  patch-404
  put-404
  delete-404)
