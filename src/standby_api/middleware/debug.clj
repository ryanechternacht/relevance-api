(ns standby-api.middleware.debug)

(defn- wrap-debug-impl [handler {:keys [config request-method uri] :as request}]
  (let [print? (-> config :debug :print?)]
    (when print? (println "request" request-method uri request))
    (let [response (handler request)]
      (when print? (println "response" request-method uri response))
      response)))

(defn wrap-debug [h] (partial #'wrap-debug-impl h))
