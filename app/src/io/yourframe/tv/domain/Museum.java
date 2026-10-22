package io.yourframe.tv.domain;

/** A museum whose open collection the Catalogue draws from; its name is the Caption's credit line. */
public final class Museum {
    public final String id, name, shortName, license;

    public Museum(String id, String name, String shortName, String license) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.license = license;
    }
}
