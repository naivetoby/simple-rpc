package vip.toby.rpc.client;

import java.util.ArrayList;
import java.util.List;

/**
 * RpcClientRegistry
 *
 * @author toby
 */
public class RpcClientRegistry {

    private final List<Class<?>> registrations = new ArrayList<>();

    public void addRegistration(Class<?> registration) {
        this.registrations.add(registration);
    }

    protected List<Class<?>> getRegistrations() {
        return this.registrations;
    }

}
