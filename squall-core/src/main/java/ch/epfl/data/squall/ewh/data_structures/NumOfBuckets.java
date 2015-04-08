/*
 * Copyright (c) 2011-2015 EPFL DATA Laboratory
 * Copyright (c) 2014-2015 The Squall Collaboration (see NOTICE)
 *
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package ch.epfl.data.squall.ewh.data_structures;

public class NumOfBuckets {
    private int _xNumOfBuckets, _yNumOfBuckets;

    public NumOfBuckets(int xNumOfBuckets, int yNumOfBuckets) {
	_xNumOfBuckets = xNumOfBuckets;
	_yNumOfBuckets = yNumOfBuckets;
    }

    public int getXNumOfBuckets() {
	return _xNumOfBuckets;
    }

    public int getYNumOfBuckets() {
	return _yNumOfBuckets;
    }
}