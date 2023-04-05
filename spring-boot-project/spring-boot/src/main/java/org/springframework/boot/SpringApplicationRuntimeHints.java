package org.springframework.boot;

import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;

class SpringApplicationRuntimeHints extends BindableRuntimeHintsRegistrar {

    SpringApplicationRuntimeHints() {
        super(SpringApplication.class);
    }

}
