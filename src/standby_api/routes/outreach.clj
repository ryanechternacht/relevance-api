(ns standby-api.routes.outreach
  (:require [compojure.core :as cpj]
            [standby-api.data.outreach :as outreach]
            [ring.util.http-response :as response]))

(def GET-outreach 
  (cpj/GET "/v0.1/outreach" {:keys [db user]}
    (if user
      (response/ok (outreach/get-by-user db (:email user)))
      (response/unauthorized))))

(def POST-outreach
  (cpj/POST "/v0.1/outreach" {:keys [db body]}
    (response/ok (outreach/create-outreach db body))))
