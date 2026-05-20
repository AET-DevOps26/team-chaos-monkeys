package com.foundflow.founditem.repository;

import java.time.LocalDate;

public interface BucketCountView {

    LocalDate getBucketStart();

    long getCount();
}
