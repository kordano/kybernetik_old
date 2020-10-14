(ns kybernetik.utils
  (:import [java.util Date]
           [java.text SimpleDateFormat]))

(def format (SimpleDateFormat. "yyyy-MM-dd"))

(def month-format (SimpleDateFormat. "yyyy-MM"))

(def precise-format (SimpleDateFormat. "yyyy-MM-dd-HH:mm:ss"))

(defn str->date [date-string]
  (.parse format date-string))

(defn date->str [date]
  (.format format date))

(defn date->month-str [date]
  (.format month-format date))

(defn precise-str->date [date-string]
  (.parse precise-format date-string))