(ns elephantlaboratories.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [elephantlaboratories.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[elephantlaboratories started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[elephantlaboratories has shut down successfully]=-"))
   :middleware wrap-dev})
