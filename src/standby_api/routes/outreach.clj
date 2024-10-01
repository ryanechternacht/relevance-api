(ns standby-api.routes.outreach
  (:require [compojure.core :as cpj]
            [standby-api.data.outreach :as outreach]
            [ring.util.http-response :as response]))

(def GET-outreach
  (cpj/GET "/v0.1/outreach" [statuses :as {:keys [db user]}]
    (let [statuses-as-vec (cond
                            (not statuses) nil
                            (vector? statuses) statuses
                            :else [statuses])]
      (if user
        (response/ok (outreach/get-by-user db (:email user) statuses-as-vec))
        (response/unauthorized)))))

(def POST-outreach
  (cpj/POST "/v0.1/outreach" {:keys [db body]}
    (response/ok (outreach/create-outreach db body))))

(def PATCH-outreach
  (cpj/PATCH "/v0.1/outreach/:uuid" [uuid :as {:keys [db body user]}]
    ;; TODO enforce this is the user's outreach
    (if user
      (response/ok (outreach/update-outreach db uuid body))
      (response/unauthorized))))

(def POST-outreach-reply
  (cpj/POST "/v0.1/outreach/:uuid/reply" [uuid :as {:keys [db user config]}]
    ;; TODO enforce this is the user's outreach
    (if user
      (response/ok (outreach/reply! (:google-api config) db user uuid))
      (response/unauthorized))))
