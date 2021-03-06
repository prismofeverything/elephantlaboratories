(ns elephantlaboratories.server
  (:require
   [clojure.edn :as edn] 
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.pprint :as pprint]
   [clojure.java.jdbc :as j]
   [manifold.stream :as stream]
   [aleph.http :as http]
   [ring.middleware.resource :as resource]
   [ring.middleware.content-type :as content-type]
   [ring.middleware.reload :as reload]
   [taoensso.timbre :as log]
   [antlers.core :as antlers]
   [polaris.core :as polaris]
   [elephantlaboratories.mongo :as mongo]
   [elephantlaboratories.page :as page]
   [elephantlaboratories.sol :as sol]
   [elephantlaboratories.sol-previous :as sol-previous]
   [elephantlaboratories.inventory :as inventory]
   [elephantlaboratories.think :as think]
   [elephantlaboratories.deck :as deck]))

(defn wrap-route-for
  [handler routes]
  (fn [request]
    (handler
     (assoc request
       :route-for
       (fn [key params]
         (polaris/reverse-route routes key params))))))

;; (defn chronicle
;;   [request])

(defn base-routes
  [config]
  [["/" :home (page/page "index" {:title "Elephant Laboratories"})]
   (sol/sol-routes config)
   sol-previous/sol-routes
   ;; ["/chronicle" :chronicle chronicle]
   think/think-routes
   (deck/all-game-routes ["chronovore"])])

(defn app
  [routes]
  (let [built (polaris/build-routes routes)]
    (-> (polaris/router built)
        (wrap-route-for built)
        (resource/wrap-resource "communal")
        (resource/wrap-resource "public")
        (content-type/wrap-content-type)
        (reload/wrap-reload))))

(defn start
  ([] (start {}))
  ([config]
   (let [mongo (mongo/connect! {:database "elephant-laboratories"})]
     (http/start-server
      (app (base-routes {:mongo mongo}))
      {:port (or (:port config) 21112)}))))

(defn sample-now
  []
  (let [now (java.util.Date.)]
    {:year (+ 1900 (.getYear now))
     :month (inc (.getMonth now))
     :day (.getDate now)}))

(defn emit-orders
  [db last-order]
  (let [charges (mongo/find-all db "charges")
        flat (map inventory/flatten-charge charges)
        recent (drop last-order flat)
        csv (inventory/maps->csv recent (map keyword inventory/funagain-header))
        {:keys [year month day]} (sample-now)
        path (format "sol-orders-%04d-%02d-%02d.csv" year month day)]
    (spit path csv)
    (count charges)))

(defn -main
  [& args]
  (println "^^^ elephant laboratories up ^^^")
  (start {})
  (while true
    (Thread/sleep 1111)))
