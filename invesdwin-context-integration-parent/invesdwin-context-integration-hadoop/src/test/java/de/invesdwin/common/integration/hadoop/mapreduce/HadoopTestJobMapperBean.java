package de.invesdwin.common.integration.hadoop.mapreduce;

import javax.annotation.concurrent.Immutable;
import javax.inject.Named;

@Named
@Immutable
public class HadoopTestJobMapperBean {

    public boolean test() {
        return true;
    }

}
