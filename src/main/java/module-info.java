module appxi.dictionary.api {
    requires appxi.shared;

    requires static org.apache.commons.compress;
    requires org.tukaani.xz;

    exports org.appxi.dictionary;
}