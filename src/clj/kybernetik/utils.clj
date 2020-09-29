(ns kybernetik.utils
  (:import [java.util Date]
           [java.text SimpleDateFormat]))

(def format (SimpleDateFormat. "yyyy-MM-dd"))

(defn str->date [date-string]
  (.parse format date-string))

(defn date->str [date]
  (.format format date))
