;;
;;
;;  Copyright 2013 Netflix, Inc.
;;
;;     Licensed under the Apache License, Version 2.0 (the "License");
;;     you may not use this file except in compliance with the License.
;;     You may obtain a copy of the License at
;;
;;         http://www.apache.org/licenses/LICENSE-2.0
;;
;;     Unless required by applicable law or agreed to in writing, software
;;     distributed under the License is distributed on an "AS IS" BASIS,
;;     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;;     See the License for the specific language governing permissions and
;;     limitations under the License.
;;
;;

(ns pigpen.io
  "Commands to load, store, and mock data.

  Note: Most of these are present in pigpen.core. Normally you should use those instead.
"
  (:refer-clojure :exclude [load-string constantly])
  (:require [pigpen.raw :as raw]
            [pigpen.code :as code]
            [pigpen.pig :as pig]))

(set! *warn-on-reflection* true)

(defmacro load-binary
  "Loads data stored in the pigpen binary format. This is generally not used
unless debugging scripts."
  ([location] `(load-binary ~location [~'value]))
  ([location fields]
    `(raw/load$ ~location '~fields raw/default-storage {})))

(defn load-string*
  "The base for load-string, load-clj, and load-json. The parameters requires
and f specify a conversion function to apply to each input row."
  [location requires f]
  (->
    (raw/load$ location ['value] raw/string-storage {:cast "chararray"})
    (raw/bind$ requires `(pigpen.pig/map->bind ~f) {:field-type-in :native})))

(defn load-string
  "Loads data from a file. Each line is returned as a string.

  Example:

    (pig/load-string \"input.txt\")

  See also: pigpen.core/load-tsv, pigpen.core/load-clj, pigpen.core/load-json
"
  {:added "0.2.3"}
  [location]
  (load-string* location [] 'clojure.core/identity))

(defn load-tsv
  "Loads data from a tsv file. Each line is returned as a vector of strings,
split by the specified regex delimiter. The default delimiter is #\"\\t\".

  Example:

    (pig/load-tsv \"input.tsv\")
    (pig/load-tsv \"input.csv\" #\",\")

  See also: pigpen.core/load-string, pigpen.core/load-clj, pigpen.core/load-json
"
  {:added "0.1.0"}
  ([location] (load-tsv location "\t"))
  ([location delimiter]
    (load-string* location '[pigpen.extensions.core] `(fn [~'s] (if ~'s (pigpen.extensions.core/structured-split ~'s ~delimiter))))))

(defn load-csv
  "Loads data from a csv file. Each line is returned as a vector of strings,
split according to RFC4180. The default separator is \\, and quote is \\\".

  Example:

    (pig/load-csv \"input.csv\")
    (pig/load-tsv \"input.csv\" \\, \\\")

  See also: pigpen.core/load-string, pigpen.core/load-tsv, pigpen.core/load-clj, pigpen.core/load-json
"
  {:added "0.2.12"}
  ([location] (load-csv location \, \"))
  ([location separator quotor]
    (load-string* location '[clojure.data.csv] `(fn [~'s] (if ~'s (first (clojure.data.csv/read-csv ~'s :separator ~separator :quote ~quotor)))))))

(defn load-clj
  "Loads clojure data from a file. Each line should contain one value and will
be parsed using clojure.edn/read-string into a value.

  Example:

    (pig/load-clj \"input.clj\")

  See also: pigpen.core/load-string, pigpen.core/load-tsv, pigpen.core/load-json

  See: https://github.com/edn-format/edn
"
  {:added "0.1.0"}
  [location]
  (load-string* location '[clojure.edn] 'clojure.edn/read-string))

(defmacro load-json
  "Loads json data from a file. Each line should contain one value and will be
parsed using clojure.data.json/read-str into a value. Options can be passed to
read-str as a map. The default options used are {:key-fn keyword}.

  Example:

    (pig/load-json \"input.json\")

  See also: pigpen.core/load-string, pigpen.core/load-tsv, pigpen.core/load-clj
"
  {:added "0.2.3"}
  ([location] `(load-json ~location {:key-fn keyword}))
  ([location opts]
    (let [opts' (code/trap-values #{:key-fn :value-fn} opts)]
      `(load-string* ~location '[clojure.data.json]
                     `(fn [~'~'s] (clojure.data.json/read-str ~'~'s ~@~@opts'))))))

(defn load-lazy
  "Loads data from a tsv file. Each line is returned as a lazy seq, split by
the specified delimiter. The default delimiter is \\t.

  See also: pigpen.core/load-tsv
"
  {:added "0.1.0"}
  ([location] (load-lazy location "\t"))
  ([location delimiter]
    (load-string* location '[pigpen.extensions.core] `(fn [~'s] (pigpen.extensions.core/lazy-split ~'s ~delimiter)))))

(defn store-binary
  "Stores data in the PigPen binary format. This is generally not used
unless debugging scripts."
  [location relation]
  (raw/store$ relation location raw/default-storage {}))

(defn store-string*
  "The base for store-string, store-clj, and store-json. The parameters requires
and f specify a conversion function to apply to each output row."
  [location requires f relation]
  (-> relation
    (raw/bind$ requires `(pigpen.pig/map->bind ~f)
               {:args (:fields relation), :field-type-out :native})
    (raw/store$ location raw/default-storage {})))

(defn store-string
  "Stores the relation into location as a string. Each value is written as a
single line.

  Example:

    (pig/store-string \"output.txt\" foo)

  See also: pigpen.core/store-tsv, pigpen.core/store-clj, pigpen.core/store-json
"
  {:added "0.2.3"}
  [location relation]
  (store-string* location [] 'clojure.core/str relation))

(defn store-tsv
  "Stores the relation into location as a tab-delimited file. Thus, each input
value must be sequential. Complex values are stored as edn (clojure format).
Single string values are not quoted. You may optionally pass a different delimiter.

  Example:

    (pig/store-tsv \"output.tsv\" foo)
    (pig/store-tsv \"output.csv\" \",\" foo)

  See also: pigpen.core/store-string, pigpen.core/store-clj, pigpen.core/store-json
"
  {:added "0.1.0"}
  ([location relation] (store-tsv location "\t" relation))
  ([location delimiter relation]
    ;; TODO is there a more efficient way to split strings? Something without a regex.
    (store-string* location [] `(fn [~'s] (clojure.string/join ~delimiter (map print-str ~'s))) relation)))

(defn store-clj
  "Stores the relation into location using edn (clojure format). Each value is
written as a single line.

  Example:

    (pig/store-clj \"output.clj\" foo)

  See also: pigpen.core/store-string, pigpen.core/store-tsv, pigpen.core/store-json

  See: https://github.com/edn-format/edn
"
  {:added "0.1.0"}
  [location relation]
  (store-string* location [] 'clojure.core/pr-str relation))

(defmacro store-json
  "Stores the relation into location using clojure.data.json. Each value is
written as a single line. Options can be passed to write-str as a map.

  Example:

    (pig/store-json \"output.json\" foo)

  See also: pigpen.core/store-string, pigpen.core/store-tsv, pigpen.core/store-clj
"
  {:added "0.2.3"}
  ([location relation] `(store-json ~location {} ~relation))
  ([location opts relation]
    (let [opts' (code/trap-values #{:key-fn :value-fn} opts)]
      `(store-string* ~location '[clojure.data.json]
                      `(fn [~'~'s] (clojure.data.json/write-str ~'~'s ~@~@opts'))
                      ~relation))))

(defn return
  "Returns a constant set of data as a pigpen relation. This is useful for
testing, but not supported in generated scripts. The parameter 'data' must be a
sequence. The values of 'data' can be any clojure type.

  Example:

    (pig/constantly [1 2 3])
    (pig/constantly [{:a 123} {:b 456}])

  See also: pigpen.core/constantly
"
  {:added "0.1.0"}
  [data]
  (raw/return$
    (for [d data]
      (pig/freeze-vals {'value d}))
    ['value]))

(defn return-raw
  "Returns a constant set of data for script debugging and testing.
For internal use only."
  [data]
  {:pre [(first data)]}
  (raw/return$ data (keys (first data))))

(defn constantly
  "Returns a function that takes any number of arguments and returns a constant
set of data as if it had been loaded by pigpen. This is useful for testing,
but not supported in generated scripts. The parameter 'data' must be a sequence.
The values of 'data' can be any clojure type.

  Example:

    (pig/constantly [1 2 3])
    (pig/constantly [{:a 123} {:b 456}])

  See also: pigpen.core/return
"
  {:added "0.1.0"}
  [data]
  (clojure.core/constantly
    (return data)))
