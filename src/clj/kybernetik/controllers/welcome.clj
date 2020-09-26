(ns kybernetik.controllers.welcome
  (:require
   [kybernetik.layout :as layout]
   [buddy.auth :refer [authenticated?]]
   [ring.util.response :as rur]
   [kybernetik.db.core :as db]))


(defn index [request]
  (if (authenticated? request)
    (layout/render
     request
     (layout/welcome request))
    (rur/redirect "/sign-in")))

