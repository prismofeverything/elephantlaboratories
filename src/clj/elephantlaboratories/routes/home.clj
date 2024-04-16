(ns elephantlaboratories.routes.home
  (:require
   [elephantlaboratories.mongo :as db]
   [elephantlaboratories.layout :as layout]
   [elephantlaboratories.middleware :as middleware]
   [ring.util.response :as response]
   [ring.util.http-response :as http-response]))

(def tts-five-player-id 2939621161)
(def tts-four-player-id 2940599922)

(defn home-page
  [request]
  (layout/render
   request
   "home.html"
   {:current-page "home"}))

(defn sol-page
  [page request]
  (layout/render
   request
   "sol.html"
   {:current-page page}))

(defn sign-up
  [db request]
  (let [params (:params request)]
    (println "sign up! " params)
    (db/insert! db :mailing-list params)
    (response/response {:ok true :status :success})))

(defn organism-page
  [page request]
  (layout/render
   request
   "organism.html"
   {:current-page page}))

(defn home-routes
  [db]
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/sol" {:get (partial sol-page "sol-home")}]
   ["/sol/story" {:get (partial sol-page "sol-story")}]
   ["/sol/worlds" {:get (partial sol-page "sol-worlds")}]
   ["/sol/background" (partial sol-page "sol-background")]
   ["/sol/sign-up" {:get (partial sol-page "sol-buy")}]
   ;; ["/sol/buy" {:get (partial sol-page "sol-buy")}]
   ["/sol/thanks" {:get (partial sol-page "sol-thanks")}]
   ["/sol/mailing-list" {:post (partial sign-up db)}]
   ["/sol/complete" {:get (partial sol-page "sol-complete")}]
   ["/organism" {:get (partial organism-page "organism-home")}]])

