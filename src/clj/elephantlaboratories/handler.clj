(ns elephantlaboratories.handler
  (:require
   [elephantlaboratories.mongo :as db]
   [elephantlaboratories.middleware :as middleware]
   [elephantlaboratories.layout :refer [error-page]]
   [elephantlaboratories.routes.home :refer [home-routes]]
   [reitit.ring :as ring]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.webjars :refer [wrap-webjars]]
   [elephantlaboratories.env :refer [defaults]]
   [mount.core :as mount]))

(mount/defstate init-app
  :start ((or (:init defaults) (fn [])))
  :stop  ((or (:stop defaults) (fn []))))

(def mongo-connection
  {:host "localhost"
   :port 27017
   :database "elephantlaboratories"})

(mount/defstate app-routes
  :start
  (ring/ring-handler
    (ring/router
     (let [db (db/connect! mongo-connection)]
       [(home-routes db)]))
    (ring/routes
      (ring/create-resource-handler
        {:path "/"})
      (wrap-content-type
        (wrap-webjars (constantly nil)))
      (ring/redirect-trailing-slash-handler {:method :strip})
      (ring/create-default-handler
        {:not-found
         (constantly (error-page {:status 404, :title "404 - Page not found"}))
         :method-not-allowed
         (constantly (error-page {:status 405, :title "405 - Not allowed"}))
         :not-acceptable
         (constantly (error-page {:status 406, :title "406 - Not acceptable"}))}))))

(defn app []
  (middleware/wrap-base #'app-routes))
