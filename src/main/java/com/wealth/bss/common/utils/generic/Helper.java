package com.wealth.bss.common.utils.generic;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.wealth.bss.common.model.dto.integration.AccountDetailsIntegrationDTO;

@Component
public class Helper {

	static Random random = new Random();
	private static final Gson gson =  new GsonBuilder()
		    .registerTypeAdapter(LocalDate.class, new JsonDeserializer<LocalDate>() {
		        @Override
		        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
		                throws JsonParseException {
		            return LocalDate.parse(json.getAsString());
		        }
		    })
		    .registerTypeAdapter(LocalDate.class, new JsonSerializer<LocalDate>() {
		        @Override
		        public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
		            return new JsonPrimitive(src.toString()); // ISO-8601
		        }
		    })
		    .create();

	public static void printAllFields(Object obj) {
		Class<?> clazz = obj.getClass();
		System.out.println("===== Printing fields for: " + clazz.getSimpleName() + " ===== code");
		while (clazz != null) {
			for (Field field : clazz.getDeclaredFields()) {
				field.setAccessible(true);
				try {
					Object value = field.get(obj);
					System.out.println(field.getName() + " = " + value);
				} catch (IllegalAccessException e) {
					System.out.println(field.getName() + " = [ACCESS ERROR]");
				}
			}
			clazz = clazz.getSuperclass(); // Move to superclass to print inherited fields
		}
		System.out.println("================================================");
	}

	public static void printAllFields(Object obj, String name) {
		Class<?> clazz = obj.getClass();
		System.out.println("===== Printing fields for: " + clazz.getSimpleName() + " ===== code " + name);
		while (clazz != null) {
			for (Field field : clazz.getDeclaredFields()) {
				field.setAccessible(true);
				try {
					Object value = field.get(obj);
					System.out.println(field.getName() + " = " + value);
				} catch (IllegalAccessException e) {
					System.out.println(field.getName() + " = [ACCESS ERROR]");
				}
			}
			clazz = clazz.getSuperclass(); // Move to superclass to print inherited fields
		}
		System.out.println("================================================");
	}

	public static void printAllFields(List<?> objects) {
		if (objects == null || objects.isEmpty()) {
			System.out.println("Object list is null or empty");
			return;
		}
		System.out.println("===== Printing List of Objects (size = " + objects.size() + ") =====");
		int index = 0;
		for (Object obj : objects) {
			System.out.println(">>> Index: " + index++);
			printAllFields(obj);
		}
		System.out.println("================================================");
	}

	public static String generate6DigitOTP() {

		int otp = 100000 + random.nextInt(900000); // Ensures 6 digits (100000 to 999999)
		return String.valueOf(otp);
	}
	
	public static String toJson(Object object) {
        return (object == null) ? null : gson.toJson(object);
    }

    /**
     * Converts a JSON String back to a specific Object type
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return gson.fromJson(json, classOfT);
    }

    public static <T> T fromJson(String json, Type typeOfT) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return gson.fromJson(json, typeOfT);
    }


}
	
	
	

