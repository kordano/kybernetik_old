(ns kybernetik.controllers.welcome
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.db.core :as db]))


(defn index [request]
  (layout/render
   request
   (layout/welcome )))

