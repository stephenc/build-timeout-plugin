/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.build_timeout;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 */
public class BuildTimeOutUtility {
    /**
     * Construct an object from parameters input by a user.
     * 
     * Not using {@link DataBoundConstructor},
     * but using {@link Descriptor#newInstance(StaplerRequest, JSONObject)}.
     * 
     * @param req
     * @param formData
     * @param fieldName
     * @return
     * @throws hudson.model.Descriptor.FormException
     */
    public static <T> T bindJSONWithDescriptor(StaplerRequest req, JSONObject formData, String fieldName, Class<T> expectedClazz)
            throws hudson.model.Descriptor.FormException {
        formData = formData.getJSONObject(fieldName);
        if (formData == null || formData.isNullObject()) {
            return null;
        }
        if (!formData.has("stapler-class")) {
            throw new FormException("No stapler-class is specified", fieldName);
        }
        String clazzName = formData.getString("stapler-class");
        if (clazzName == null) {
            throw new FormException("No stapler-class is specified", fieldName);
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Describable<?>> clazz = (Class<? extends Describable<?>>)Jenkins.getInstance().getPluginManager().uberClassLoader.loadClass(clazzName);
            Descriptor<?> d = Jenkins.getInstance().getDescriptorOrDie(clazz);
            @SuppressWarnings("unchecked")
            T ret = (T)d.newInstance(req, formData);
            return ret;
        } catch(ClassNotFoundException e) {
            throw new FormException(
                    String.format("Failed to instantiate: class not found %s", clazzName),
                    e,
                    fieldName
            );
        } catch(ClassCastException e) {
            throw new FormException(
                    String.format("Failed to instantiate: instantiated as %s but expected %s", clazzName, expectedClazz.getName()),
                    e,
                    fieldName
            );
        }
    }
    
}
