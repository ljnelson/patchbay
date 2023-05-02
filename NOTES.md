# NOTES

# Specialized Interfaces

…are the answer to the namespace problem.

Aliasing probably needs to happen in some way.

Yes, this is definitely the way to go.

Consider:
```
package com.foo.bar;
public interface DefaultConfig {

  public String a();

}

package com.foo.baz;
public interface SpecialConfig extends com.foo.bar.DefaultConfig {

}
```

The config system now looks for configuration for `com.foo.bar.DefaultConfig` _first_, whether it is the configuration class being asked for or not.

Then the config system looks for configuration for `com.foo.baz.SpecialConfig` next, and uses the configuration for `com.foo.bar.DefaultConfig` as its defaults.

You get back a `SpecialConfig` that has been configured to supply its overriding values where appropriate, the defaults where not appropriate, and 


Here, we would expect a logical model for `DefaultConfig` and a logical model for `SpecialConfig`.  If a value for `SpecialConfig.a` is absent, then we should use `DefaultConfig.a` instead.

So does nested actually work, or was I thinking inheritance all along?

```
public interface DefaultConfig {

  public String a();

  public interface SpecialConfig { // note: no extends
  
    public String a(); // no relation, right? to DefaultConfig.a()?
  
  }

}
```

The mild confusion arises because there is only ever one object of a configuration class, so a _nested_ configuration class results in a _nested_ configuration object, and you can "find" your default by looking at your declaring class.

But inheritance is better in this situation.  With nested, you have to own both the parent and the child.  With inheritance you do not.


