(ns elephantlaboratories.routes.home
  (:require
   [elephantlaboratories.layout :as layout]
   [clojure.java.io :as io]
   [elephantlaboratories.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page
  [request]
  (layout/render
   request
   "home.html"
   {:current-page "home"}))

(defn sol-home-page
  [request]
  (layout/render
   request
   "sol.html"
   {:current-page "sol-home"}))

(defn sol-story-page
  [request]
  (layout/render
   request
   "sol.html"
   {:current-page "sol-story"}))

(defn sol-worlds-page
  [request]
  (layout/render
   request
   "sol.html"
   {:current-page "sol-worlds"}))

(defn sol-background-page
  [request]
  (layout/render
   request
   "sol.html"
   {:current-page "sol-background"}))

(defn sol-buy-page
  [request]
  (layout/render
   request
   "sol.html"
   {:current-page "sol-buy"}))

(defn sol-thanks-page
  [request]
  (layout/render
   request
   "sol.html"
   {:current-page "sol-thanks"}))

(defn home-routes
  []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/sol/" {:get sol-home-page}]
   ["/sol/story/" {:get sol-story-page}]
   ["/sol/worlds/" {:get sol-worlds-page}]
   ["/sol/background/" {:get sol-background-page}]
   ["/sol/buy/" {:get sol-buy-page}]
   ["/sol/thanks/" {:get sol-thanks-page}]])






   ;; ["/docs" {:get (fn [_]
   ;;                  (-> (response/ok (-> "docs/docs.md" io/resource slurp))
   ;;                      (response/header "Content-Type" "text/plain; charset=utf-8")))}]

