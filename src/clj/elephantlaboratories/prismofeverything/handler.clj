(ns elephantlaboratories.prismofeverything.handler
  (:require
   [elephantlaboratories.prismofeverything.routes :refer [prism-routes]]
   [elephantlaboratories.layout :refer [error-page]]
   [elephantlaboratories.middleware.formats :as formats]
   [muuntaja.middleware :refer [wrap-format wrap-params]]
   [reitit.ring :as ring]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [clojure.tools.logging :as log]))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        (error-page {:status 500 :title "Something went wrong"})))))

(defn wrap-formats [handler]
  (let [wrapped (-> handler wrap-params (wrap-format formats/instance))]
    (fn [request]
      ((if (:websocket? request) handler wrapped) request))))

(defn prism-app []
  (->
   (ring/ring-handler
    (ring/router [(prism-routes)])
    (ring/routes
     (ring/create-resource-handler {:path "/"})
     (wrap-content-type (constantly nil))
     (ring/redirect-trailing-slash-handler {:method :strip})
     (ring/create-default-handler
      {:not-found
       (constantly (error-page {:status 404 :title "404 - Page not found"}))})))
   wrap-formats
   wrap-internal-error))
