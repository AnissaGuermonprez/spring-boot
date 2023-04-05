package org.springframework.boot;

public class ConfigureMain {

    private Class<?> mainApplicationClass;

    public ConfigureMain() {

    }

    /**
     * Returns the main application class that has been deduced or explicitly
     * configured.
     * 
     * @return the main application class or {@code null}
     */
    public Class<?> getMainApplicationClass() {
        return this.mainApplicationClass;
    }

    /**
     * Set a specific main application class that will be used as a log source and
     * to
     * obtain version information. By default the main application class will be
     * deduced.
     * Can be set to {@code null} if there is no explicit application class.
     * 
     * @param mainApplicationClass the mainApplicationClass to set or {@code null}
     */
    public void setMainApplicationClass(Class<?> mainApplicationClass) {
        this.mainApplicationClass = mainApplicationClass;
    }

    public boolean isMainThread(Thread currentThread) {
		return ("main".equals(currentThread.getName()) || "restartedMain".equals(currentThread.getName()))
				&& "main".equals(currentThread.getThreadGroup().getName());
	}

}
