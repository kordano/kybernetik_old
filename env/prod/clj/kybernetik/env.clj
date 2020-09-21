(ns kybernetik.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[kybernetik started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[kybernetik has shut down successfully]=-"))
   :middleware identity})
