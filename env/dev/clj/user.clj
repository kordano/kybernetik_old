(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
   [kybernetik.config :refer [env]]
   [datahike.api :as d]
   [kybernetik.db.core :refer [conn reset-db] :as db]
   [clojure.pprint]
   [clojure.spec.alpha :as s]
   [expound.alpha :as expound]
   [mount.core :as mount]
   [kybernetik.core :refer [start-app]]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(add-tap (bound-fn* clojure.pprint/pprint))

(defn start
  "Starts application.
  You'll usually want to run this on startup."
  []
  (mount/start-without #'kybernetik.core/repl-server))

(defn stop
  "Stops application."
  []
  (mount/stop-except #'kybernetik.core/repl-server))

(defn restart
  "Restarts application."
  []
  (stop)
  (start))

(comment

  (start)

  (stop)

  (reset-db)

  (db/list-users)

  (db/list-logs)
  (db/list-vacations {:user-id 39})

  (db/list-timesheets {:user-id 32 :approved? true}))
