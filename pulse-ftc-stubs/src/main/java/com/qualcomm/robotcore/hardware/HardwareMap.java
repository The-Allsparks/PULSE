package com.qualcomm.robotcore.hardware;

import android.content.Context;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-only {@code HardwareMap} with {@code getAll} for Hub discovery.
 * Robot builds use the official FTC SDK class of the same name.
 */
public class HardwareMap {
    public Context appContext = new Context();
    private final Map<String, Object> devices = new LinkedHashMap<String, Object>();

    public void put(String name, Object device) {
        devices.put(name, device);
    }

    public <T> T get(Class<? extends T> classOrInterface, String deviceName) {
        Object found = devices.get(deviceName);
        if (found == null) {
            throw new IllegalArgumentException("Unable to find a hardware device with the name " + deviceName);
        }
        return classOrInterface.cast(found);
    }

    public <T> List<T> getAll(Class<? extends T> classOrInterface) {
        List<T> found = new ArrayList<T>();
        for (Object device : devices.values()) {
            if (classOrInterface.isInstance(device)) {
                found.add(classOrInterface.cast(device));
            }
        }
        return found;
    }

    public Set<String> getNamesOf(HardwareDevice device) {
        Set<String> names = new LinkedHashSet<String>();
        if (device == null) {
            return names;
        }
        for (Map.Entry<String, Object> entry : devices.entrySet()) {
            if (device == entry.getValue()) {
                names.add(entry.getKey());
            }
        }
        return names;
    }
}
