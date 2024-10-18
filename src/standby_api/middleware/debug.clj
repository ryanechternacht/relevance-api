(ns standby-api.middleware.debug)

(defn- remove-annoying-field
  "this field is multiline by default and messes with our logs"
  [req]
  (update req :user dissoc :public-link-message))

(defn- remove-crsf-token
  [req]
  (update req :body dissoc :__anti-forgery-token))

;; WARNING setting print? to :local will dump the entire request map which includes
;; info that should not be logged. Don't use it in prod!
;; Setting debug? to :prod will only dump the request body

(defn- wrap-debug-impl [handler {:keys [config request-method uri] :as request}]
  (let [print? (-> config :debug :print?)]
    (condp = print?
      :prod (println "request" request-method uri (-> request :body remove-crsf-token))
      :local (println "request" request-method uri (remove-annoying-field request)))
    (let [response (handler request)]
      (condp = print?
        :prod (println "response" request-method uri (:body response))
        :local (println "response" request-method uri response))
      response)))

(defn wrap-debug [h] (partial #'wrap-debug-impl h))
