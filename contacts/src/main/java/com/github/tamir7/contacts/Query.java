/*
 * Copyright 2016 Tamir Shomer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tamir7.contacts;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Query {
    private final Context context;
    private final Map<Contact.Field, Object> contains = new HashMap<>();
    private final List<Contact.Field> include = Arrays.asList(Contact.Field.values());
    private boolean hasPhoneNumber = false;

    Query(Context context) {
        this.context = context;
    }

    public Query whereContains(Contact.Field field, Object value) {
        contains.put(field, value);
        return this;
    }

    public Query hasPhoneNumber() {
        hasPhoneNumber = true;
        return this;
    }

    public Query include(Contact.Field... fields) {
        include.addAll(Arrays.asList(fields));
        return this;
    }

    public List<Contact> find() {
        Cursor c = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                buildProjection(),
                buildSelection(),
                null,
                ContactsContract.Data.DISPLAY_NAME);

        Map<Long, Contact> contactsMap = new LinkedHashMap<>();

        if (c != null) {
            while (c.moveToNext()) {
                CursorHelper helper = new CursorHelper(c);
                Long contactId = helper.getContactId();
                Contact contact = contactsMap.get(contactId);
                if (contact == null) {
                    contact = new Contact();
                    contactsMap.put(contactId, contact);
                }

                updateContact(contact, helper);
            }

            c.close();
        }

        return new ArrayList<>(contactsMap.values());
    }

    private void updateContact(Contact contact, CursorHelper helper) {
        String displayName = helper.getDisplayName();
        if (displayName != null) {
            contact.addDisplayName(displayName);
        }

        String photoUri = helper.getPhotoUri();
        if (photoUri != null) {
            contact.addPhotoUri(photoUri);
        }

        String mimeType = helper.getMimeType();
        if (mimeType.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
            PhoneNumber phoneNumber = helper.getPhoneNumber();
            if (phoneNumber != null) {
                contact.addPhoneNumber(phoneNumber);
            }
        } else if (mimeType.equals(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
            Email email = helper.getEmail();
            if (email != null) {
                contact.addEmail(email);
            }
        }
    }

    private String[] buildProjection() {
        Set<String> projection = new HashSet<>();

        for (Contact.AbstractField field : Contact.InternalField.values()) {
            projection.addAll(field.getColumns());
        }

        for (Contact.AbstractField field : include) {
            projection.addAll(field.getColumns());
        }

        return projection.toArray(new String[projection.size()]);
    }

    private String buildSelection() {
        Where where = null;

        if (hasPhoneNumber) {
            where = addWhere(null, Where.notEqualTo(ContactsContract.Data.HAS_PHONE_NUMBER, 0));
        }

        Set<String> mimeTypes = new HashSet<>();

        for (Contact.AbstractField field : Contact.InternalField.values()) {
            mimeTypes.add(field.getMimeType());
        }

        for (Contact.AbstractField field : include) {
            mimeTypes.add(field.getMimeType());
        }

        where = addWhere(where, Where.in(ContactsContract.Data.MIMETYPE, new ArrayList<>(mimeTypes)));

        for (Map.Entry<Contact.Field, Object> entry : contains.entrySet()) {
            where = addWhere(where, Where.contains(entry.getKey().getColumns().get(0), entry.getValue()));
        }

        Log.e("Temp", where.toString());
        return where.toString();
    }

    private Where addWhere(Where where, Where otherWhere) {
        return where == null ? otherWhere : where.and(otherWhere);
    }
}