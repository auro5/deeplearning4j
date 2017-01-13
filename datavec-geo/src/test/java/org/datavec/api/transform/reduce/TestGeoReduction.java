/*
 *  * Copyright 2017 Skymind, Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 */

package org.datavec.api.transform.reduce;

import org.datavec.api.writable.*;
import org.datavec.api.transform.ColumnType;
import org.datavec.api.transform.schema.Schema;
import org.junit.Test;

import java.util.*;
import org.datavec.api.transform.ReduceOp;
import org.datavec.api.transform.reduce.geo.CoordinatesReduction;

import static org.junit.Assert.assertEquals;

/**
 * @author saudet
 */
public class TestGeoReduction {

    @Test
    public void testCustomReductions(){

        List<List<Writable>> inputs = new ArrayList<>();
        inputs.add(Arrays.asList((Writable)new Text("someKey"), new Text("1#5")));
        inputs.add(Arrays.asList((Writable)new Text("someKey"), new Text("2#6")));
        inputs.add(Arrays.asList((Writable)new Text("someKey"), new Text("3#7")));
        inputs.add(Arrays.asList((Writable)new Text("someKey"), new Text("4#8")));

        List<Writable> expected = Arrays.asList((Writable)new Text("someKey"), new Text("10.0#26.0"));

        Schema schema = new Schema.Builder()
                .addColumnString("key")
                .addColumnString("coord")
                .build();

        Reducer reducer = new Reducer.Builder(ReduceOp.Count)
                .keyColumns("key")
                .customReduction("coord",new CoordinatesReduction("coordSum", ReduceOp.Sum, "#"))
                .build();

        reducer.setInputSchema(schema);

        List<Writable> out = reducer.reduce(inputs);

        assertEquals(2,out.size());
        assertEquals(expected, out);

        //Check schema:
        String[] expNames = new String[]{"key", "coordSum"};
        ColumnType[] expTypes = new ColumnType[]{ColumnType.String, ColumnType.String};
        Schema outSchema = reducer.transform(schema);

        assertEquals(2, outSchema.numColumns());
        for( int i=0; i<2; i++ ){
            assertEquals(expNames[i], outSchema.getName(i));
            assertEquals(expTypes[i], outSchema.getType(i));
        }
    }
}
