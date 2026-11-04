package com.redmagic.fps;
interface IPropertyService {
    String[] readProperty() = 0;
    String[] writeProperty(int value) = 1;
    void destroy() = 16777114;
}
