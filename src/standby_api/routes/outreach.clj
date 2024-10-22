(ns standby-api.routes.outreach
  (:require [compojure.core :as cpj]
            [standby-api.data.outreach :as outreach]
            [standby-api.middleware.csrf :as csrf]
            [ring.util.http-response :as response]))

(def GET-outreach
  (cpj/GET "/v0.1/outreach" [status :as {:keys [db user]}]
    (if user
      (response/ok (outreach/get-by-user db (:email user) status))
      (response/unauthorized))))

(def PATCH-outreach
  (cpj/PATCH "/v0.1/outreach/:uuid" [uuid :as {:keys [db body user] :as req}]
    ;; TODO enforce this is the user's outreach
    (cond
      (not user) (response/unauthorized)
      (not (csrf/valid-csrf? req)) (csrf/csrf-failure-response)
      :else (response/ok (outreach/update-outreach db uuid body)))))

(def POST-outreach-reply
  (cpj/POST "/v0.1/outreach/:uuid/reply" [uuid :as {:keys [db user config body] :as req}]
    ;; TODO enforce this is the user's outreach
    (cond
      (not user) (response/unauthorized)
      (not (csrf/valid-csrf? req)) (csrf/csrf-failure-response)
      :else (response/ok (outreach/reply! (:google-api config) db user uuid body)))))

;; public, so no csrf needed
(def POST-outreach
  (cpj/POST "/v0.1/outreach" {:keys [db body]}
    (response/ok (outreach/create-outreach db body))))

(def GET-outreach-uuid
  (cpj/GET "/v0.1/outreach/:uuid" [uuid :as {:keys [db]}]
    (response/ok (outreach/get-by-uuid db uuid))))
