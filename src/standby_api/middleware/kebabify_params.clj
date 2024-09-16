(ns standby-api.middleware.kebabify-params
  (:require [camel-snake-kebab.extras :as csk-extras]
            [camel-snake-kebab.core :as csk]))

(defn- wrap-kebabify-params-impl [handler request]
  (-> request
      (update :params #(csk-extras/transform-keys csk/->kebab-case-keyword %))
      handler))

(defn wrap-kebabify-params [h] (partial #'wrap-kebabify-params-impl h))
