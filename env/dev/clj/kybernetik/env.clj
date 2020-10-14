(ns kybernetik.env
  (:require
   [selmer.parser :as parser]
   [clojure.tools.logging :as log]
   [kybernetik.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[kybernetik started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[kybernetik has shut down successfully]=-"))
   :middleware wrap-dev})
