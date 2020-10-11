(ns kybernetik.utils
  (:import [java.util Date]
           [java.text SimpleDateFormat]))

(def format (SimpleDateFormat. "yyyy-MM-dd"))

(def month-format (SimpleDateFormat. "yyyy-MM"))

(defn str->date [date-string]
  (.parse format date-string))

(defn date->str [date]
  (.format format date))

(defn date->month-str [date]
  (.format month-format date))