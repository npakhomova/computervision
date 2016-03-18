import org.junit.Test;
import utils.DataCollectionJobUtils;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by npakhomova on 3/8/16.
 */
public class DataCollectionTest {

    @Test
    public void buildUrlTest() {

        assertEquals("https://stars.macys.com/preview/02/98/01/04/final/2980104-214x261.jpg", DataCollectionJobUtils.buildURL(2980104));

    }
}
