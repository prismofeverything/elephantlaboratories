(ns elephantlaboratories.app
  (:require [elephantlaboratories.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
