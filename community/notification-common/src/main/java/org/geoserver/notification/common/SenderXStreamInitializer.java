package org.geoserver.notification.common;

import java.util.List;

import org.geoserver.notification.common.sender.NotificationSender;
import org.geoserver.platform.GeoServerExtensions;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;

public class SenderXStreamInitializer implements NotificationXStreamInitializer {

    /**
     * Alias for sender tag of in xml configuration
     */
    public String name;

    /**
     * Class to use for sender with filed 'name'
     */
    public Class<? extends NotificationSender> clazz;

    /**
     * Define an alias for the {@link DefaultNotificationProcessor#sender sender}<br>
     * Define a class for the {@link NotificationSender}<br>
     * An example of sender configuration section in notifier.xml is:
     *
     * <pre>
     *  {@code
     *  <genericProcessor>
     *          <fanoutSender>
     *          ...
     *          </fanoutSender>
     *  </genericProcessor>
     *  }
     * </pre>
     *
     * @param xs XStream object
     *
     */
    public SenderXStreamInitializer(String name, Class<? extends NotificationSender> clazz) {
        super();
        this.name = name;
        this.clazz = clazz;
    }

    @Override
    public void init(XStream xs) {
        xs.aliasAttribute(DefaultNotificationProcessor.class, "sender", name);
        xs.registerLocalConverter(DefaultNotificationProcessor.class, "sender",
                new SenderConverter(xs.getMapper(), xs.getReflectionProvider(), this));
    }

    /**
     *
     * @author Alessio Fabiani, GeoSolutions S.A.S.
     *
     */
    static public class SenderConverter extends ReflectionConverter {

        private SenderXStreamInitializer senderXStreamInitializer;

        /**
         * @param mapper
         * @param reflectionProvider
         * @param senderXStreamInitializer
         */
        public SenderConverter(Mapper mapper, ReflectionProvider reflectionProvider,
                SenderXStreamInitializer senderXStreamInitializer) {
            super(mapper, reflectionProvider);
            this.senderXStreamInitializer = senderXStreamInitializer;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public boolean canConvert(Class clazz) {
            return clazz.isAssignableFrom(senderXStreamInitializer.clazz);
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            NotificationSender sender = null;
            String nodeName = reader.getNodeName();

            List<SenderXStreamInitializer> serializers = GeoServerExtensions.extensions(SenderXStreamInitializer.class);

            for(SenderXStreamInitializer serializer : serializers) {
                if (serializer.name.equals(nodeName)) {
                    try {
                        sender = serializer.clazz.newInstance();
                        sender = (NotificationSender) context.convertAnother(sender, serializer.clazz);
                        break;
                    } catch (InstantiationException e) {
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            return sender;
        }
    }
}
