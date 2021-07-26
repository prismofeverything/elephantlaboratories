(ns elephantlaboratories.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [elephantlaboratories.core-test]))

(doo-tests 'elephantlaboratories.core-test)

